FROM docker:29.8.0-cli@sha256:eccaacfeed644c7de222ff047483568cb988dde95476fbaaf10ea2d04921bb66 AS docker
FROM python:3.12.11-alpine3.22@sha256:efcdfa6a6b2fd2afb9c7dfa9a5b288a6f68338b5cfdebe6b637d986067d85757
COPY --from=docker /usr/local/bin/docker /usr/local/bin/docker
COPY --from=docker /usr/local/libexec/docker/cli-plugins /usr/local/libexec/docker/cli-plugins
RUN apk add --no-cache libpq openssl coreutils util-linux curl && \
    python3 -m venv /opt/ansible
COPY requirements.txt requirements.yml /ops/
RUN /opt/ansible/bin/pip install --no-cache-dir -r /ops/requirements.txt && \
    /opt/ansible/bin/ansible-galaxy collection install -r /ops/requirements.yml -p /usr/share/ansible/collections
ENV PATH="/opt/ansible/bin:$PATH" ANSIBLE_NOCOLOR=1 ANSIBLE_DISPLAY_ARGS_TO_STDOUT=False ANSIBLE_DIFF_ALWAYS=False
WORKDIR /ops
ENTRYPOINT ["ansible-playbook", "-i", "localhost,", "-c", "local"]
