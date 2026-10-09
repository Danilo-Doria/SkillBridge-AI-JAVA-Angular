const fs = require('fs');
let ts = fs.readFileSync('frontend/src/app/features/payment-modal/payment-modal.ts', 'utf8');

ts = ts.replace('submitPayment() {', '// Valida lógica de negocio de la tarjeta y envía el payload a la pasarela (backend)\n  submitPayment() {');

fs.writeFileSync('frontend/src/app/features/payment-modal/payment-modal.ts', ts);
