const fs = require('fs');
let java = fs.readFileSync('backend/src/main/java/com/riwi/skillbridge/domain/model/Booking.java', 'utf8');

java = java.replace('public Booking cancel() {', '// Modificado para permitir cancelar tanto reservas CREATED como CONFIRMED\\n    public Booking cancel() {');

fs.writeFileSync('backend/src/main/java/com/riwi/skillbridge/domain/model/Booking.java', java);
