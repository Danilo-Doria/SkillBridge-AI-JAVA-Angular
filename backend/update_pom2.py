with open('pom.xml', 'r') as f:
    lines = f.readlines()

dep = """    <dependency>
      <groupId>org.springframework.kafka</groupId>
      <artifactId>spring-kafka</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.kafka</groupId>
      <artifactId>spring-kafka-test</artifactId>
      <scope>test</scope>
    </dependency>
"""

in_management = False
for i, line in enumerate(lines):
    if '<dependencyManagement>' in line:
        in_management = True
    if '</dependencyManagement>' in line:
        in_management = False
    
    if '<dependencies>' in line and not in_management:
        lines.insert(i + 1, dep)
        break

with open('pom.xml', 'w') as f:
    f.writelines(lines)
