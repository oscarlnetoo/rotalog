/**
 * Authorize Middleware factory
 *
 * createAuthorize(config) devolve requireRole(...roles):
 *  - enforce: 403 { error: 'Acesso negado' } sem req.user ou com role fora da lista
 *  - report:  registra a negação e segue (nunca rejeita)
 *  - off:     não faz nada
 */

function createAuthorize(config) {
    return function requireRole() {
        var roles = Array.prototype.slice.call(arguments);

        return function authorize(req, res, next) {
            if (config.mode === 'off') {
                return next();
            }

            if (req.user && roles.indexOf(req.user.role) !== -1) {
                return next();
            }

            console.warn('[AUTH] ' + req.method + ' ' + (req.originalUrl || req.url) +
                ' negado: role ' + (req.user ? req.user.role : 'ausente') +
                ' fora de [' + roles.join(', ') + ']');

            if (config.mode === 'report') {
                return next();
            }

            return res.status(403).json({ error: 'Acesso negado' });
        };
    };
}

module.exports = { createAuthorize };
