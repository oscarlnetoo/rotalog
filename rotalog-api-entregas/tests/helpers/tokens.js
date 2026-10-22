const jwt = require('jsonwebtoken');

const SECRET = 'test-secret-with-at-least-32-bytes!!';

const config = Object.freeze({
    mode: 'enforce',
    secret: SECRET,
    issuer: 'rotalog',
    audience: 'rotalog-api-entregas',
    algorithms: Object.freeze(['HS256'])
});

// Assina um token válido por padrão; `claims`/`options` trocam o conteúdo.
// Passe null em `options` para omitir uma opção (ex.: subject, expiresIn).
function signToken({ claims = {}, options = {}, secret = SECRET } = {}) {
    const payload = { role: 'operador', nome: 'Maria', ...claims };
    const signOptions = {
        algorithm: 'HS256',
        subject: '42',
        issuer: 'rotalog',
        audience: 'rotalog-api-entregas',
        expiresIn: '1h',
        ...options
    };
    Object.keys(signOptions).forEach((k) => signOptions[k] === null && delete signOptions[k]);
    Object.keys(payload).forEach((k) => payload[k] === undefined && delete payload[k]);
    return jwt.sign(payload, secret, signOptions);
}

// Token com alg "none" e sem assinatura.
function unsignedToken() {
    const b64 = (o) => Buffer.from(JSON.stringify(o)).toString('base64url');
    return b64({ alg: 'none', typ: 'JWT' }) + '.' +
        b64({ sub: '1', role: 'admin', iss: 'rotalog', aud: 'rotalog-api-entregas', exp: Math.floor(Date.now() / 1000) + 3600 }) + '.';
}

module.exports = { SECRET, config, signToken, unsignedToken };
