const fs = require('fs');
let ts = fs.readFileSync('frontend/src/app/features/my-bookings.component.ts', 'utf8');

ts = ts.replace('canCancel(scheduledAt: string): boolean {', '// Verifica si faltan más de 24 horas para la sesión para permitir la cancelación\n  canCancel(scheduledAt: string): boolean {');
ts = ts.replace('cancelBooking(bookingId: string): void {', '// Llama al servicio para cancelar la reserva y actualiza la lista localmente\n  cancelBooking(bookingId: string): void {');
ts = ts.replace('openPaymentModal(booking: Booking): void {', '// Abre el modal de pago guardando la reserva seleccionada\n  openPaymentModal(booking: Booking): void {');
ts = ts.replace('onPaymentSuccess(): void {', '// Se ejecuta al aprobarse el pago, marcando la reserva como confirmada sin recargar la página\n  onPaymentSuccess(): void {');

fs.writeFileSync('frontend/src/app/features/my-bookings.component.ts', ts);
