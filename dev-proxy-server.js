const http = require("http");
const fs = require("fs");
const path = require("path");

const PORT = 5500;
const HOST = "localhost";
const API_HOST = "3.37.6.60";
const API_PORT = 8080;
const ROOT_DIR = __dirname;

const contentTypes = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".svg": "image/svg+xml",
  ".ico": "image/x-icon"
};

function proxyApiRequest(clientRequest, clientResponse) {
  const headers = { ...clientRequest.headers };
  headers.host = `${API_HOST}:${API_PORT}`;

  const proxyRequest = http.request(
    {
      hostname: API_HOST,
      port: API_PORT,
      path: clientRequest.url,
      method: clientRequest.method,
      headers
    },
    (proxyResponse) => {
      clientResponse.writeHead(proxyResponse.statusCode || 500, proxyResponse.headers);
      proxyResponse.pipe(clientResponse);
    }
  );

  proxyRequest.on("error", (error) => {
    console.error("API proxy error:", error.message);
    clientResponse.writeHead(502, { "Content-Type": "text/plain; charset=utf-8" });
    clientResponse.end("API proxy failed.");
  });

  clientRequest.pipe(proxyRequest);
}

function serveStaticFile(clientRequest, clientResponse) {
  const requestUrl = new URL(clientRequest.url, `http://${HOST}:${PORT}`);
  const requestPath = requestUrl.pathname === "/" ? "/index.html" : decodeURIComponent(requestUrl.pathname);
  const filePath = path.resolve(ROOT_DIR, `.${requestPath}`);

  if (!filePath.startsWith(ROOT_DIR)) {
    clientResponse.writeHead(403, { "Content-Type": "text/plain; charset=utf-8" });
    clientResponse.end("Forbidden");
    return;
  }

  fs.readFile(filePath, (error, data) => {
    if (error) {
      clientResponse.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" });
      clientResponse.end("Not found");
      return;
    }

    clientResponse.writeHead(200, {
      "Content-Type": contentTypes[path.extname(filePath)] || "application/octet-stream"
    });
    clientResponse.end(data);
  });
}

const server = http.createServer((request, response) => {
  if (request.url.startsWith("/api/")) {
    proxyApiRequest(request, response);
    return;
  }

  serveStaticFile(request, response);
});

server.listen(PORT, HOST, () => {
  console.log(`SNOW dev proxy running at http://${HOST}:${PORT}`);
  console.log(`Proxying /api/* to http://${API_HOST}:${API_PORT}`);
});
