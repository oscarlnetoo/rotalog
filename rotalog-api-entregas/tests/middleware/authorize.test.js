const { createAuthorize } = require('../../src/middleware/authorize');

describe('requireRole', () => {
    let res;
    let next;

    function run(mode, user, roles = ['admin', 'operador']) {
        const req = { method: 'POST', originalUrl: '/api/entregas', user };
        createAuthorize({ mode })(...roles)(req, res, next);
    }

    beforeEach(() => {
        res = { status: jest.fn().mockReturnThis(), json: jest.fn().mockReturnThis() };
        next = jest.fn();
        jest.spyOn(console, 'warn').mockImplementation(() => {});
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    describe('enforce', () => {
        it('chama next quando o papel é permitido', () => {
            run('enforce', { id: '1', role: 'operador' });

            expect(next).toHaveBeenCalledTimes(1);
            expect(res.status).not.toHaveBeenCalled();
        });

        it('retorna 403 quando o papel não é permitido', () => {
            run('enforce', { id: '1', role: 'servico' });

            expect(res.status).toHaveBeenCalledWith(403);
            expect(res.json).toHaveBeenCalledWith({ error: 'Acesso negado' });
            expect(next).not.toHaveBeenCalled();
        });

        it('retorna 403 quando não há req.user', () => {
            run('enforce', undefined);

            expect(res.status).toHaveBeenCalledWith(403);
            expect(next).not.toHaveBeenCalled();
            expect(console.warn).toHaveBeenCalledWith(expect.stringContaining('role ausente'));
        });
    });

    describe('report', () => {
        it('registra a negação mas chama next', () => {
            run('report', { id: '1', role: 'servico' });

            expect(next).toHaveBeenCalledTimes(1);
            expect(res.status).not.toHaveBeenCalled();
            expect(console.warn).toHaveBeenCalledWith(expect.stringContaining('role servico'));
        });

        it('não registra nada quando o papel é permitido', () => {
            run('report', { id: '1', role: 'admin' });

            expect(next).toHaveBeenCalledTimes(1);
            expect(console.warn).not.toHaveBeenCalled();
        });
    });

    describe('off', () => {
        it('pula a autorização, mesmo sem req.user', () => {
            run('off', undefined);

            expect(next).toHaveBeenCalledTimes(1);
            expect(res.status).not.toHaveBeenCalled();
            expect(console.warn).not.toHaveBeenCalled();
        });
    });

    it('usa req.url quando originalUrl não existe', () => {
        const req = { method: 'GET', url: '/x' };
        createAuthorize({ mode: 'enforce' })('admin')(req, res, next);

        expect(console.warn).toHaveBeenCalledWith(expect.stringContaining('GET /x negado'));
    });
});
