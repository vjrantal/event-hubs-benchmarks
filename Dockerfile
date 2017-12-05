FROM torosent/python-qpid-proton:0.18.1
COPY requirements.txt requirements.txt
RUN pip3 install -r requirements.txt
COPY benchmark.py benchmark.py
CMD python3 benchmark.py
