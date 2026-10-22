/**
 * Auth Middleware factory
 *
 * FIXME: Sem validação real do token (próximo passo: tokenService)
 * FIXME: Sem refresh token
 * FIXME: Sem role-based access control
 */

function createAuthMiddleware(config) {
    return function authMiddleware(req, res, next) {
        if (config.mode === 'off') {
            return next();
        }

        var authHeader = req.headers.authorization;

        if (!authHeader) {
            return res.status(401).json({ error: 'Token não fornecido' });
        }

        // FIXME: Apenas verifica se header existe, não valida o token
        var parts = authHeader.split(' ');
        if (parts.length !== 2 || parts[0] !== 'Bearer') {
            return res.status(401).json({ error: 'Token mal formatado' });
        }

        var token = parts[1];

        // FIXME: "Validação" fake - apenas verifica se não está vazio
        if (!token || token.length < 10) {
            return res.status(401).json({ error: 'Token inválido' });
        }

        // FIXME: User fake
        req.user = { id: 1, nome: 'Admin', role: 'admin' };

        next();
    };
}

module.exports = { createAuthMiddleware };
