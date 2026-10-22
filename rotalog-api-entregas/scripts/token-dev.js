/**
 * Gera um JWT assinado para testes manuais (requests.http).
 * Uso: npm run token:dev -- --role operador --sub 1 [--nome "Fulano"]
 * Recusa rodar com NODE_ENV=production.
 */

require('dotenv').config();
const jwt = require('jsonwebtoken');

function getArg(name, fallback) {
    const index = process.argv.indexOf('--' + name);
    return index !== -1 && process.argv[index + 1] ? process.argv[index + 1] : fallback;
}

if (process.env.NODE_ENV === 'production') {
    console.error('token:dev não pode ser usado com NODE_ENV=production');
    process.exit(1);
}

const secret = process.env.JWT_SECRET;
if (!secret || Buffer.byteLength(secret) < 32) {
    console.error('JWT_SECRET ausente ou menor que 32 bytes no .env');
    process.exit(1);
}

const role = getArg('role', 'operador');
const sub = getArg('sub', 'dev-user');
const nome = getArg('nome', 'Dev ' + role);
const algorithm = (process.env.JWT_ALGORITHMS || 'HS256').split(',')[0].trim();

const token = jwt.sign({ role: role, nome: nome }, secret, {
    algorithm: algorithm,
    subject: sub,
    issuer: process.env.JWT_ISSUER || 'rotalog',
    audience: process.env.JWT_AUDIENCE || 'rotalog-api-entregas',
    expiresIn: '1h'
});

console.log(token);
