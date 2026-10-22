/**
 * Auth Middleware factory
 *
 * Modos (config.mode):
 *  - enforce: rejeita com 401 qualquer requisição sem token válido
 *  - report:  verifica e define req.user quando válido, registra falhas, nunca rejeita
 *  - off:     não faz nada (apenas desenvolvimento local)
 *
 * FIXME: Sem refresh token
 */

const { verifyToken } = require('../services/tokenService');

function createAuthMiddleware(config) {
    return function authMiddleware(req, res, next) {
        if (config.mode === 'off') {
            return next();
        }

        function reject(status, message, reason) {
            // Nunca registrar o token, apenas o motivo
            console.warn('[AUTH] ' + req.method + ' ' + (req.originalUrl || req.url) + ' rejeitado: ' + reason);
            if (config.mode === 'report') {
                return next();
            }
            res.set('WWW-Authenticate', 'Bearer');
            return res.status(status).json({ error: message });
        }

        var authHeader = req.headers.authorization;

        if (!authHeader) {
            return reject(401, 'Token não fornecido', 'TokenNaoFornecido');
        }

        var parts = authHeader.split(' ');
        if (parts.length !== 2 || parts[0].toLowerCase() !== 'bearer') {
            return reject(401, 'Token mal formatado', 'TokenMalFormatado');
        }

        var token = parts[1];
        if (!token) {
            return reject(401, 'Token inválido', 'TokenVazio');
        }

        try {
            req.user = verifyToken(token, config);
        } catch (err) {
            return reject(401, 'Token inválido', err.name);
        }

        next();
    };
}

module.exports = { createAuthMiddleware };
