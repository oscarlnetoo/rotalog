const { createAuthMiddleware } = require('../../src/middleware/auth');

describe('authMiddleware', () => {
    const originalEnv = process.env.NODE_ENV;
    let res;
    let next;

    function buildReq(authorization) {
        return { headers: authorization === undefined ? {} : { authorization } };
    }

    beforeEach(() => {
        res = { status: jest.fn().mockReturnThis(), json: jest.fn().mockReturnThis() };
        next = jest.fn();
        jest.spyOn(console, 'log').mockImplementation(() => {});
    });

    afterEach(() => {
        process.env.NODE_ENV = originalEnv;
        jest.restoreAllMocks();
    });

    describe('AUTH_MODE=off', () => {
        it('ignora a autenticação', () => {
            const req = buildReq();

            createAuthMiddleware({ mode: 'off' })(req, res, next);

            expect(next).toHaveBeenCalledTimes(1);
            expect(res.status).not.toHaveBeenCalled();
            expect(req.user).toBeUndefined();
        });

        it('não depende de NODE_ENV', () => {
            process.env.NODE_ENV = 'production';
            const req = buildReq();

            createAuthMiddleware({ mode: 'off' })(req, res, next);

            expect(next).toHaveBeenCalledTimes(1);
        });
    });

    describe('AUTH_MODE=enforce', () => {
        const authMiddleware = (req, res, next) => createAuthMiddleware({ mode: 'enforce' })(req, res, next);

        it('retorna 401 quando o header Authorization não existe', () => {
            authMiddleware(buildReq(), res, next);

            expect(res.status).toHaveBeenCalledWith(401);
            expect(res.json).toHaveBeenCalledWith({ error: 'Token não fornecido' });
            expect(next).not.toHaveBeenCalled();
        });

        it.each([
            ['sem o prefixo Bearer', 'abcdefghijklmnop'],
            ['com mais de duas partes', 'Bearer abcdefghij extra'],
            ['com esquema diferente de Bearer', 'Basic abcdefghijklmnop'],
            ['com Bearer em minúsculas', 'bearer abcdefghijklmnop'],
        ])('retorna 401 quando o header está mal formatado (%s)', (_descricao, header) => {
            authMiddleware(buildReq(header), res, next);

            expect(res.status).toHaveBeenCalledWith(401);
            expect(res.json).toHaveBeenCalledWith({ error: 'Token mal formatado' });
            expect(next).not.toHaveBeenCalled();
        });

        it.each([
            ['vazio', 'Bearer '],
            ['curto demais (9 caracteres)', 'Bearer 123456789'],
        ])('retorna 401 quando o token é %s', (_descricao, header) => {
            authMiddleware(buildReq(header), res, next);

            expect(res.status).toHaveBeenCalledWith(401);
            expect(res.json).toHaveBeenCalledWith({ error: 'Token inválido' });
            expect(next).not.toHaveBeenCalled();
        });

        it('aceita um token com 10 caracteres, define req.user e chama next', () => {
            const req = buildReq('Bearer 1234567890');

            authMiddleware(req, res, next);

            expect(req.user).toEqual({ id: 1, nome: 'Admin', role: 'admin' });
            expect(next).toHaveBeenCalledTimes(1);
            expect(res.status).not.toHaveBeenCalled();
        });

        it('aceita um token longo sem validar assinatura nem expiração', () => {
            const req = buildReq('Bearer qualquer.coisa.nao.jwt.mas.longa');

            authMiddleware(req, res, next);

            expect(req.user).toBeDefined();
            expect(next).toHaveBeenCalledTimes(1);
        });
    });
});
