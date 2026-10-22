const { createAuthMiddleware } = require('../../src/middleware/auth');
const { config, signToken, unsignedToken } = require('../helpers/tokens');

describe('authMiddleware', () => {
    let res;
    let next;

    function buildReq(authorization) {
        return {
            method: 'GET',
            originalUrl: '/api/entregas',
            headers: authorization === undefined ? {} : { authorization }
        };
    }

    function run(cfg, req) {
        createAuthMiddleware(cfg)(req, res, next);
    }

    beforeEach(() => {
        res = {
            status: jest.fn().mockReturnThis(),
            json: jest.fn().mockReturnThis(),
            set: jest.fn().mockReturnThis()
        };
        next = jest.fn();
        jest.spyOn(console, 'warn').mockImplementation(() => {});
        jest.spyOn(console, 'log').mockImplementation(() => {});
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    function expect401(message) {
        expect(res.status).toHaveBeenCalledWith(401);
        expect(res.json).toHaveBeenCalledWith({ error: message });
        expect(res.set).toHaveBeenCalledWith('WWW-Authenticate', 'Bearer');
        expect(next).not.toHaveBeenCalled();
    }

    describe('AUTH_MODE=off', () => {
        it('ignora a autenticação, mesmo com NODE_ENV=production', () => {
            const original = process.env.NODE_ENV;
            process.env.NODE_ENV = 'production';
            const req = buildReq();

            run({ ...config, mode: 'off' }, req);

            process.env.NODE_ENV = original;
            expect(next).toHaveBeenCalledTimes(1);
            expect(res.status).not.toHaveBeenCalled();
            expect(req.user).toBeUndefined();
        });
    });

    describe('AUTH_MODE=enforce', () => {
        it('aceita um token válido e define exatamente o req.user das claims', () => {
            const req = buildReq('Bearer ' + signToken());

            run(config, req);

            expect(req.user).toEqual({ id: '42', nome: 'Maria', role: 'operador' });
            expect(next).toHaveBeenCalledTimes(1);
            expect(res.status).not.toHaveBeenCalled();
        });

        it('aceita o esquema bearer em minúsculas', () => {
            const req = buildReq('bearer ' + signToken());

            run(config, req);

            expect(next).toHaveBeenCalledTimes(1);
            expect(req.user.id).toBe('42');
        });

        it('retorna 401 quando o header Authorization não existe', () => {
            run(config, buildReq());

            expect401('Token não fornecido');
        });

        it.each([
            ['sem o prefixo Bearer', 'abcdefghijklmnop'],
            ['com mais de duas partes', 'Bearer abcdefghij extra'],
            ['com esquema diferente de Bearer', 'Basic abcdefghijklmnop'],
        ])('retorna 401 quando o header está mal formatado (%s)', (_descricao, header) => {
            run(config, buildReq(header));

            expect401('Token mal formatado');
        });

        it.each([
            ['vazio', () => 'Bearer '],
            ['curto', () => 'Bearer 123456789'],
            ['longo mas não JWT', () => 'Bearer qualquer.coisa.nao.jwt.mas.longa'],
            ['expirado', () => 'Bearer ' + signToken({ options: { expiresIn: -120 } })],
            ['com assinatura errada', () => 'Bearer ' + signToken({ secret: 'outro-segredo-com-mais-de-32-bytes!!!' })],
            ['com HS512 quando só HS256 é permitido', () => 'Bearer ' + signToken({ options: { algorithm: 'HS512' } })],
            ['com iss errado', () => 'Bearer ' + signToken({ options: { issuer: 'outro' } })],
            ['com aud errado', () => 'Bearer ' + signToken({ options: { audience: 'outro' } })],
            ['sem sub', () => 'Bearer ' + signToken({ options: { subject: null } })],
            ['sem role', () => 'Bearer ' + signToken({ claims: { role: undefined } })],
            ['com alg none', () => 'Bearer ' + unsignedToken()],
        ])('retorna 401 Token inválido quando o token é %s', (_descricao, header) => {
            const req = buildReq(header());

            run(config, req);

            expect401('Token inválido');
            expect(req.user).toBeUndefined();
        });

        it('registra o motivo da rejeição sem vazar o token', () => {
            const token = signToken({ options: { expiresIn: -120 } });

            run(config, buildReq('Bearer ' + token));

            expect(console.warn).toHaveBeenCalledWith(expect.stringContaining('GET /api/entregas rejeitado: TokenExpiredError'));
            expect(console.warn.mock.calls.flat().join(' ')).not.toContain(token);
        });

        it('usa req.url quando originalUrl não existe', () => {
            run(config, { method: 'GET', url: '/x', headers: {} });

            expect(console.warn).toHaveBeenCalledWith(expect.stringContaining('GET /x rejeitado'));
        });
    });

    describe('AUTH_MODE=report', () => {
        const reportConfig = { ...config, mode: 'report' };

        it('define req.user e chama next com token válido', () => {
            const req = buildReq('Bearer ' + signToken());

            run(reportConfig, req);

            expect(req.user).toEqual({ id: '42', nome: 'Maria', role: 'operador' });
            expect(next).toHaveBeenCalledTimes(1);
            expect(console.warn).not.toHaveBeenCalled();
        });

        it.each([
            ['sem header', undefined],
            ['mal formatado', 'Basic abc'],
            ['inválido', 'Bearer nao.e.jwt'],
        ])('nunca rejeita (%s), apenas registra sem o token', (_descricao, header) => {
            const req = buildReq(header);

            run(reportConfig, req);

            expect(next).toHaveBeenCalledTimes(1);
            expect(res.status).not.toHaveBeenCalled();
            expect(req.user).toBeUndefined();
            expect(console.warn).toHaveBeenCalledTimes(1);
            expect(console.warn.mock.calls[0][0]).not.toContain('nao.e.jwt');
        });
    });
});
