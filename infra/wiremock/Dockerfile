FROM wiremock/wiremock:3.13.2
COPY --chmod=644 mappings/ /home/wiremock/mappings/
RUN mkdir -p /home/wiremock/__files \
    && useradd --system --no-create-home --home-dir /home/wiremock --user-group wiremock \
    && chmod -R u=rwX,go=rX /home/wiremock/mappings /home/wiremock/__files \
    && chown -R wiremock:wiremock /home/wiremock
USER wiremock
HEALTHCHECK --interval=10s --timeout=5s --retries=10 --start-period=10s CMD curl -f http://localhost:8080/__admin/health || exit 1
