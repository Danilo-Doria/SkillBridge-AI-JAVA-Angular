import yaml

with open('src/main/resources/application.yml', 'r') as f:
    app_yml = yaml.safe_load(f)

app_yml['spring']['kafka'] = {
    'bootstrap-servers': '${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}',
    'producer': {
        'value-serializer': 'org.springframework.kafka.support.serializer.JsonSerializer',
        'key-serializer': 'org.apache.kafka.common.serialization.StringSerializer'
    },
    'consumer': {
        'group-id': '${KAFKA_CONSUMER_GROUP:skillbridge-audit-group}',
        'auto-offset-reset': 'earliest',
        'value-deserializer': 'org.springframework.kafka.support.serializer.JsonDeserializer',
        'key-deserializer': 'org.apache.kafka.common.serialization.StringDeserializer',
        'properties': {
            'spring.json.trusted.packages': '*'
        }
    }
}
if 'app' not in app_yml:
    app_yml['app'] = {}
app_yml['app']['kafka'] = {
    'topic': {
        'booking-events': '${KAFKA_TOPIC_BOOKING_EVENTS:booking-events}'
    }
}

with open('src/main/resources/application.yml', 'w') as f:
    yaml.dump(app_yml, f, sort_keys=False)
