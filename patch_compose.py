import yaml
from collections import OrderedDict

# This script will insert kafka back into docker-compose.yml
# But wait, python's yaml module might reformat the whole file. 
# It's better to just write the file completely from scratch based on the exact output I saw earlier.
