const { loadAuthConfig } = require('../../src/config/auth');

const SECRET = 'a'.repeat(32);

describe('loadAuthConfig', () => {
    it('usa enforce como modo padrão e aplica os valores padrão', () => {
        const config = loadAuthConfig({ JWT_SECRET: SECRET });

        expect(config).toEqual({
            mode: 'enforce',
            secret: SECRET,
            issuer: 'rotalog',
            audience: 'rotalog-api-entregas',
            algorithms: ['HS256']
        });
    });

    it('retorna um objeto congelado', () => {
        const config = loadAuthConfig({ JWT_SECRET: SECRET });

        expect(Object.isFrozen(config)).toBe(true);
        expect(Object.isFrozen(config.algorithms)).toBe(true);
    });

    it('lê issuer, audience e algoritmos (CSV) do ambiente', () => {
        const config = loadAuthConfig({
            JWT_SECRET: SECRET,
            JWT_ISSUER: 'emissor',
            JWT_AUDIENCE: 'publico',
            JWT_ALGORITHMS: 'HS256, HS384'
        });

        expect(config.issuer).toBe('emissor');
        expect(config.audience).toBe('publico');
        expect(config.algorithms).toEqual(['HS256', 'HS384']);
    });

    it.each(['foo', 'ENFORCE', ''])('rejeita AUTH_MODE inválido (%p) quando informado', (mode) => {
        if (mode === '') {
            // string vazia cai no padrão enforce
            expect(loadAuthConfig({ AUTH_MODE: mode, JWT_SECRET: SECRET }).mode).toBe('enforce');
            return;
        }
        expect(() => loadAuthConfig({ AUTH_MODE: mode, JWT_SECRET: SECRET })).toThrow(/AUTH_MODE inválido/);
    });

    it('rejeita AUTH_MODE=off em produção', () => {
        expect(() => loadAuthConfig({ AUTH_MODE: 'off', NODE_ENV: 'production' })).toThrow(/production/);
    });

    it('aceita AUTH_MODE=off fora de produção sem segredo', () => {
        expect(loadAuthConfig({ AUTH_MODE: 'off', NODE_ENV: 'development' }).mode).toBe('off');
    });

    it.each(['enforce', 'report'])('exige JWT_SECRET no modo %s', (mode) => {
        expect(() => loadAuthConfig({ AUTH_MODE: mode })).toThrow(/JWT_SECRET/);
    });

    it('rejeita segredo com menos de 32 bytes', () => {
        expect(() => loadAuthConfig({ JWT_SECRET: 'a'.repeat(31) })).toThrow(/JWT_SECRET/);
    });

    it('mede o segredo em bytes, não em caracteres', () => {
        // 16 caracteres de 2 bytes = 32 bytes
        expect(loadAuthConfig({ JWT_SECRET: 'é'.repeat(16) }).mode).toBe('enforce');
    });

    it('NODE_ENV não altera o modo', () => {
        expect(loadAuthConfig({ NODE_ENV: 'development', JWT_SECRET: SECRET }).mode).toBe('enforce');
    });
});
