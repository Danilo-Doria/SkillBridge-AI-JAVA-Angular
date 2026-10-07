with open('src/main/resources/application.yml', 'r') as f:
    lines = f.readlines()

kafka_spring = """  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
    consumer:
      group-id: ${KAFKA_CONSUMER_GROUP:skillbridge-audit-group}
      auto-offset-reset: earliest
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      properties:
        spring.json.trusted.packages: '*'
"""

kafka_app = """  kafka:
    topic:
      booking-events: ${KAFKA_TOPIC_BOOKING_EVENTS:booking-events}
"""

for i, line in enumerate(lines):
    if line.startswith('management:'):
        lines.insert(i, kafka_spring + '\n')
        break

lines.append('\n' + kafka_app)

with open('src/main/resources/application.yml', 'w') as f:
    f.writelines(lines)
