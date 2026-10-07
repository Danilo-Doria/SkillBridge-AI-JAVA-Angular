import re

with open('pom.xml', 'r') as f:
    pom = f.read()

dep = """
    <dependency>
      <groupId>org.springframework.kafka</groupId>
      <artifactId>spring-kafka</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.kafka</groupId>
      <artifactId>spring-kafka-test</artifactId>
      <scope>test</scope>
    </dependency>"""

pom = re.sub(r'(<dependencies>\s+<dependency>)', f'<dependencies>{dep}\n    <dependency>', pom, count=1)

with open('pom.xml', 'w') as f:
    f.write(pom)
