/**
 * Token Service - único ponto de verificação de JWT.
 * Sem dependência de Express. Trocar HS256 por RS256/JWKS muda só a config e este módulo.
 */

const jwt = require('jsonwebtoken');

const REQUIRED_CLAIMS = ['sub', 'role', 'exp'];

function verifyToken(token, config) {
    const payload = jwt.verify(token, config.secret, {
        algorithms: config.algorithms,
        issuer: config.issuer,
        audience: config.audience,
        clockTolerance: 30
    });

    REQUIRED_CLAIMS.forEach(function(claim) {
        if (payload[claim] === undefined || payload[claim] === null || payload[claim] === '') {
            throw new jwt.JsonWebTokenError('claim obrigatória ausente: ' + claim);
        }
    });

    return { id: payload.sub, nome: payload.nome, role: payload.role };
}

module.exports = { verifyToken };
