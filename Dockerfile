FROM torosent/python-qpid-proton
RUN pip3 install lxml beautifulsoup4 azure applicationinsights
COPY benchmark.py benchmark.py
RUN pip3 install git+https://github.com/CatalystCode/azure-event-hubs-python.git@63ae7844fa8650c78ed3a4e6beef8e8c780d4ad4
CMD python3 benchmark.py
