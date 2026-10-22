const request = require('supertest');

// Handlers must not touch the database: every model method resolves to null.
jest.mock('../../src/models', () => {
    const fakeModel = () => new Proxy({}, { get: () => jest.fn().mockResolvedValue(null) });
    return { Entrega: fakeModel(), Rastreamento: fakeModel() };
});

describe('montagem das rotas (caracterização, NODE_ENV=production)', () => {
    const originalEnv = { ...process.env };
    let app;

    beforeAll(() => {
        process.env.NODE_ENV = 'production';
        process.env.AUTH_MODE = 'enforce';
        process.env.JWT_SECRET = 'test-secret-with-at-least-32-bytes!!';
        jest.spyOn(console, 'log').mockImplementation(() => {});
        jest.spyOn(console, 'error').mockImplementation(() => {});
        app = require('../../src/index');
    });

    afterAll(() => {
        process.env = originalEnv;
        jest.restoreAllMocks();
    });

    describe('rotas públicas hoje', () => {
        it('GET /api/health responde 200', async () => {
            const res = await request(app).get('/api/health');
            expect(res.status).toBe(200);
        });

        it.each([
            ['GET', '/api/rastreamento/1'],
            ['GET', '/api/rastreamento/1/ultimo'],
            ['GET', '/api/rastreamento/pedido/PED-1'],
            ['POST', '/api/rastreamento/1'],
        ])('%s %s não exige autenticação', async (method, path) => {
            const res = await request(app)[method.toLowerCase()](path).send({});
            expect(res.status).not.toBe(401);
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
