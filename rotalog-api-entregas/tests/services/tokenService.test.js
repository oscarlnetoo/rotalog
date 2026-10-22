const { verifyToken } = require('../../src/services/tokenService');
const { config, signToken, unsignedToken } = require('../helpers/tokens');

describe('verifyToken', () => {
    it('retorna o usuário a partir das claims verificadas', () => {
        expect(verifyToken(signToken(), config)).toEqual({ id: '42', nome: 'Maria', role: 'operador' });
    });

    it('aceita token sem a claim opcional nome', () => {
        const token = signToken({ claims: { nome: undefined } });
        expect(verifyToken(token, config)).toEqual({ id: '42', nome: undefined, role: 'operador' });
    });

    it('aceita token expirado dentro da tolerância de 30s', () => {
        const token = signToken({ options: { expiresIn: -10 } });
        expect(verifyToken(token, config).id).toBe('42');
    });

    it.each([
        ['expirado', () => signToken({ options: { expiresIn: -120 } }), 'TokenExpiredError'],
        ['com assinatura errada', () => signToken({ secret: 'outro-segredo-com-mais-de-32-bytes!!!' }), 'JsonWebTokenError'],
        ['com iss errado', () => signToken({ options: { issuer: 'outro' } }), 'JsonWebTokenError'],
        ['com aud errado', () => signToken({ options: { audience: 'outro' } }), 'JsonWebTokenError'],
        ['HS512 quando só HS256 é permitido', () => signToken({ options: { algorithm: 'HS512' } }), 'JsonWebTokenError'],
        ['sem sub', () => signToken({ options: { subject: null } }), 'JsonWebTokenError'],
        ['sem role', () => signToken({ claims: { role: undefined } }), 'JsonWebTokenError'],
        ['sem exp', () => signToken({ options: { expiresIn: null } }), 'JsonWebTokenError'],
        ['alg none', () => unsignedToken(), 'JsonWebTokenError'],
        ['malformado', () => 'nao.e.jwt', 'JsonWebTokenError'],
    ])('rejeita token %s', (_nome, build, errorName) => {
        let error;
        try {
            verifyToken(build(), config);
        } catch (err) {
            error = err;
        }
        expect(error).toBeDefined();
        expect(error.name).toBe(errorName);
    });

    it('usa a lista de algoritmos da config, não a do token', () => {
        const hs512 = signToken({ options: { algorithm: 'HS512' } });
        const permissive = { ...config, algorithms: ['HS256', 'HS512'] };
        expect(verifyToken(hs512, permissive).role).toBe('operador');
    });
});
