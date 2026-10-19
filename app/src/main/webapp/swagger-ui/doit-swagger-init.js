// Khởi tạo Swagger UI (tách khỏi index.html để CSP script-src 'self' không cần 'unsafe-inline').
/* global axios, SwaggerUIBundle, SwaggerUIStandalonePreset, document */
const AlwaysEnableTryItOutPlugin = function () {
  return {
    components: {
      TryItOutButton: () => null,
    },
  };
};

const getBearerToken = () => {
  let authToken = localStorage.getItem('jhi-authenticationToken') || sessionStorage.getItem('jhi-authenticationToken');
  if (authToken) {
    authToken = JSON.parse(authToken);
    return `Bearer ${authToken}`;
  }
  return null;
};

function createAxiosConfig(serverBaseUri) {
  return {
    timeout: 5000,
    baseURL: serverBaseUri,
    headers: { Authorization: getBearerToken() },
  };
}

async function fetchUrls(serverBaseUri, axiosConfig, baseUrl) {
  let urls;

  if (!urls || urls.length === 0) {
    try {
      const response = await axios.get('/management/jhiopenapigroups', axiosConfig);
      if (Array.isArray(response.data)) {
        urls = response.data.map(({ group, description }) => ({ name: description, url: `${serverBaseUri}${baseUrl}/${group}` }));
      }
    } catch (error) {
      console.error('Failed to fetch default API docs', error);
    }
  }
  console.log(`Swagger urls`, urls);

  return urls?.length ? urls : [{ name: 'default', url: `${serverBaseUri}${baseUrl}` }];
}

function sortUrls(urls) {
  if (!urls) return urls;

  urls.sort((a, b) => compareUrls(a.name.toLowerCase(), b.name.toLowerCase()));

  return urls;
}

function compareUrls(nameA, nameB) {
  if (nameA.includes('(default)')) return -1;
  if (nameB.includes('(default)')) return 1;
  if (nameA.includes('(management)')) return -1;
  if (nameB.includes('(management)')) return 1;
  return nameA.localeCompare(nameB);
}

function initializeSwaggerUI(urls, baseUrl) {
  // Build a system
  const ui = SwaggerUIBundle({
    urls,
    url: baseUrl,
    dom_id: '#swagger-ui',
    deepLinking: true,
    filter: true,
    layout: 'StandaloneLayout',
    withCredentials: true,
    presets: [SwaggerUIBundle.presets.apis, SwaggerUIStandalonePreset],
    plugins: [SwaggerUIBundle.plugins.DownloadUrl, AlwaysEnableTryItOutPlugin],
    tryItOutEnabled: true,
    requestInterceptor(req) {
      req.headers['Authorization'] = getBearerToken();
      // Remove the sample Swagger UI request body if present
      if (req.method === 'GET' && req.body === '{"additionalProp1":"string","additionalProp2":"string","additionalProp3":"string"}') {
        req.body = undefined;
      }
      return req;
    },
  });

  globalThis.ui = ui;
}

globalThis.onload = async function () {
  const baseUrl = 'v3/api-docs';
  const serverBaseUri = document.baseURI.replace('swagger-ui/', '');
  const axiosConfig = createAxiosConfig(serverBaseUri);
  const urls = await fetchUrls(serverBaseUri, axiosConfig, baseUrl);
  const sortedUrls = sortUrls(urls);

  initializeSwaggerUI(sortedUrls, baseUrl);
};
