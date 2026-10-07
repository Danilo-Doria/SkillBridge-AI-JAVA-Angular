import re

with open('src/test/java/com/riwi/skillbridge/application/service/BookingServiceTest.java', 'r') as f:
    code = f.read()

# Add BookingEventPublisherPort to imports
code = re.sub(r'import com.riwi.skillbridge.application.port.out.NotificationPublisherPort;', 
              'import com.riwi.skillbridge.application.port.out.NotificationPublisherPort;\nimport com.riwi.skillbridge.application.port.out.BookingEventPublisherPort;', code)

# Mock it in all tests
code = code.replace('NotificationPublisherPort notificationPublisher = mock(NotificationPublisherPort.class);', 
                    'NotificationPublisherPort notificationPublisher = mock(NotificationPublisherPort.class);\n        BookingEventPublisherPort eventPublisher = mock(BookingEventPublisherPort.class);')

# Update service constructor
service_sig = """    private BookingService service(
            BookingRepositoryPort bookings,
            OfferingRepositoryPort offerings,
            UserAccountPort users,
            
            NotificationPublisherPort notificationPublisher,
            BookingEventPublisherPort eventPublisher,
            Instant now)"""
code = re.sub(r'    private BookingService service\([\s\S]*?Instant now\) \{', service_sig + ' {', code)

service_ret = """        return new BookingService(
                bookings,
                offerings,
                users,
                
                cancellationPolicyAt(now),
                notificationPublisher,
                eventPublisher
        );"""
code = re.sub(r'        return new BookingService\([\s\S]*?notificationPublisher\n        \);', service_ret, code)

# Update all service() calls
code = re.sub(r'service\(\n\s*bookings,\n\s*offerings,\n\s*users,\n\s*notificationPublisher,', 
              'service(\n                        bookings,\n                        offerings,\n                        users,\n                        notificationPublisher,\n                        eventPublisher,', code)

# Fix non-formatted calls
code = code.replace('service(\n                bookings,\n                offerings,\n                users,\n                \n                notificationPublisher,\n                now\n        )',
                    'service(\n                bookings,\n                offerings,\n                users,\n                \n                notificationPublisher,\n                eventPublisher,\n                now\n        )')

with open('src/test/java/com/riwi/skillbridge/application/service/BookingServiceTest.java', 'w') as f:
    f.write(code)
