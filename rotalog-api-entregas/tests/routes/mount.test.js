const request = require('supertest');
const { SECRET, signToken } = require('../helpers/tokens');

// Handlers must not touch the database: every model method resolves to null.
jest.mock('../../src/models', () => {
    const fakeModel = () => new Proxy({}, { get: () => jest.fn().mockResolvedValue(null) });
    return { Entrega: fakeModel(), Rastreamento: fakeModel() };
});

// api-frotas não é chamado de verdade
jest.mock('../../src/services/frotasService', () => {
    const done = (...args) => args[args.length - 1](null, null);
    return {
        listarVeiculosAtivos: done,
        buscarVeiculoPorPlaca: done,
        verificarDisponibilidadeVeiculo: done,
        buscarMotoristaPorId: done
    };
});

describe('montagem das rotas e política D4 (AUTH_MODE=enforce, NODE_ENV=production)', () => {
    const originalEnv = { ...process.env };
    let app;

    beforeAll(() => {
        process.env.NODE_ENV = 'production';
        process.env.AUTH_MODE = 'enforce';
        process.env.JWT_SECRET = SECRET;
        jest.spyOn(console, 'log').mockImplementation(() => {});
        jest.spyOn(console, 'error').mockImplementation(() => {});
        jest.spyOn(console, 'warn').mockImplementation(() => {});
        app = require('../../src/index');
    });

    afterAll(() => {
        process.env = originalEnv;
        jest.restoreAllMocks();
    });

    describe('rotas públicas', () => {
        it('GET /api/health responde 200', async () => {
            const res = await request(app).get('/api/health');
            expect(res.status).toBe(200);
        });

        it.each([
            '/api/rastreamento/1',
            '/api/rastreamento/1/ultimo',
            '/api/rastreamento/pedido/PED-1',
        ])('GET %s não exige autenticação', async (path) => {
            const res = await request(app).get(path);
            expect(res.status).not.toBe(401);
            expect(res.status).not.toBe(403);
        });
    });

    describe('POST /api/rastreamento/:entregaId (fechado pelo ADR 0001)', () => {
        it('responde 401 sem token', async () => {
            const res = await request(app).post('/api/rastreamento/1').send({});
            expect(res.status).toBe(401);
            expect(res.headers['www-authenticate']).toBe('Bearer');
        });

        it('responde 403 para o papel servico', async () => {
            const res = await request(app).post('/api/rastreamento/1').set('Authorization', 'Bearer ' + signToken({ claims: { role: 'servico' } })).send({});
            expect(res.status).toBe(403);
            expect(res.body).toEqual({ error: 'Acesso negado' });
        });

        it.each(['admin', 'operador', 'motorista'])('aceita o papel %s', async (role) => {
            const res = await request(app).post('/api/rastreamento/1').set('Authorization', 'Bearer ' + signToken({ claims: { role } })).send({});
            expect(res.status).not.toBe(401);
            expect(res.status).not.toBe(403);
        });
    });

    describe('escrita em /api/entregas exige admin ou operador', () => {
        const writes = [
            ['POST', '/api/entregas'],
            ['PUT', '/api/entregas/1'],
            ['PATCH', '/api/entregas/1/status'],
            ['PATCH', '/api/entregas/1/atribuir'],
            ['DELETE', '/api/entregas/1'],
        ];

        it.each(writes)('%s %s responde 403 para servico e para motorista', async (method, path) => {
            for (const role of ['servico', 'motorista']) {
                const res = await request(app)[method.toLowerCase()](path).set('Authorization', 'Bearer ' + signToken({ claims: { role } })).send({});
                expect(res.status).toBe(403);
            }
        });

        it.each(writes)('%s %s não bloqueia admin nem operador', async (method, path) => {
            for (const role of ['admin', 'operador']) {
                const res = await request(app)[method.toLowerCase()](path).set('Authorization', 'Bearer ' + signToken({ claims: { role } })).send({});
                expect(res.status).not.toBe(401);
                expect(res.status).not.toBe(403);
            }
        });
    });

    describe('leituras autenticadas aceitam qualquer papel válido', () => {
        it.each([
            '/api/entregas',
            '/api/entregas/1',
            '/api/entregas/pedido/PED-1',
            '/api/frotas/veiculos/disponiveis',
            '/api/frotas/veiculos/ABC1D23',
            '/api/frotas/veiculos/ABC1D23/disponibilidade',
            '/api/frotas/motoristas/1',
        ])('GET %s com papel servico', async (path) => {
            const res = await request(app).get(path).set('Authorization', 'Bearer ' + signToken({ claims: { role: 'servico' } }));
            expect(res.status).not.toBe(401);
            expect(res.status).not.toBe(403);
        });
    });

    describe('rotas protegidas sem token', () => {
        it.each([
            ['GET', '/api/entregas'],
            ['GET', '/api/entregas/stats'],
            ['GET', '/api/entregas/1'],
            ['GET', '/api/entregas/pedido/PED-1'],
            ['POST', '/api/entregas'],
            ['PUT', '/api/entregas/1'],
            ['PATCH', '/api/entregas/1/status'],
            ['PATCH', '/api/entregas/1/atribuir'],
            ['DELETE', '/api/entregas/1'],
            ['GET', '/api/frotas/veiculos/disponiveis'],
            ['GET', '/api/frotas/veiculos/ABC1D23'],
            ['GET', '/api/frotas/veiculos/ABC1D23/disponibilidade'],
            ['GET', '/api/frotas/motoristas/1'],
        ])('%s %s responde 401', async (method, path) => {
            const res = await request(app)[method.toLowerCase()](path).send({});
            expect(res.status).toBe(401);
            expect(res.body).toEqual({ error: 'Token não fornecido' });
        });
    });
});
