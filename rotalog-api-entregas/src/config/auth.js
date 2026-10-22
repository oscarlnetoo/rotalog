/**
 * Auth configuration - read and validated once, at startup.
 * No fallbacks: a missing or weak secret makes the service refuse to start.
 */

const MODES = ['enforce', 'report', 'off'];
const MIN_SECRET_BYTES = 32;

function loadAuthConfig(env = process.env) {
    const mode = env.AUTH_MODE || 'enforce';

    if (!MODES.includes(mode)) {
        throw new Error('AUTH_MODE inválido: "' + mode + '" (use enforce, report ou off)');
    }

    if (mode === 'off' && env.NODE_ENV === 'production') {
        throw new Error('AUTH_MODE=off não é permitido com NODE_ENV=production');
    }

    const secret = env.JWT_SECRET;
    if (mode !== 'off' && (!secret || Buffer.byteLength(secret) < MIN_SECRET_BYTES)) {
        throw new Error('JWT_SECRET ausente ou menor que ' + MIN_SECRET_BYTES + ' bytes');
    }

    const algorithms = (env.JWT_ALGORITHMS || 'HS256')
        .split(',')
        .map(function(alg) { return alg.trim(); })
        .filter(Boolean);

    return Object.freeze({
        mode: mode,
        secret: secret,
        issuer: env.JWT_ISSUER || 'rotalog',
        audience: env.JWT_AUDIENCE || 'rotalog-api-entregas',
        algorithms: Object.freeze(algorithms)
    });
}

module.exports = { loadAuthConfig };
