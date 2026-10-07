import yaml
with open('../docker-compose.yml', 'r') as f:
    compose = yaml.safe_load(f)

compose['services']['kafka'] = {
    'image': 'confluentinc/cp-kafka:7.8.0',
    'environment': {
        'KAFKA_NODE_ID': 1,
        'KAFKA_LISTENER_SECURITY_PROTOCOL_MAP': 'CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT',
        'KAFKA_ADVERTISED_LISTENERS': 'PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092',
        'KAFKA_PROCESS_ROLES': 'broker,controller',
        'KAFKA_CONTROLLER_QUORUM_VOTERS': '1@kafka:29093',
        'KAFKA_LISTENERS': 'PLAINTEXT://kafka:29092,CONTROLLER://kafka:29093,PLAINTEXT_HOST://0.0.0.0:9092',
        'KAFKA_INTER_BROKER_LISTENER_NAME': 'PLAINTEXT',
        'KAFKA_CONTROLLER_LISTENER_NAMES': 'CONTROLLER',
        'CLUSTER_ID': 'MkU3OEVBNTcwNTJENDM2Qk',
        'KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR': 1,
        'KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR': 1,
        'KAFKA_TRANSACTION_STATE_LOG_MIN_ISR': 1,
        'KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS': 0
    },
    'volumes': ['kafka_data:/var/lib/kafka/data'],
    'ports': ['127.0.0.1:9092:9092'],
    'healthcheck': {
        'test': ["CMD", "kafka-topics", "--bootstrap-server", "localhost:9092", "--list"],
        'interval': '10s',
        'timeout': '5s',
        'retries': 15
    },
    'networks': ['skillbridge']
}
compose['volumes']['kafka_data'] = None

if 'depends_on' in compose['services']['backend']:
    compose['services']['backend']['depends_on']['kafka'] = {'condition': 'service_healthy'}

env = compose['services']['backend']['environment']
env['KAFKA_BOOTSTRAP_SERVERS'] = '${KAFKA_BOOTSTRAP_SERVERS:-kafka:29092}'

with open('../docker-compose.yml', 'w') as f:
    yaml.dump(compose, f, sort_keys=False)
