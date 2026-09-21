import request from "@/utils/request";

const data = (response) => response.data.data;
export const experienceApi = {
  status: () => request.get("/api/experience/status").then(data),
  library: () => request.get("/api/experience").then(data),
  revisions: () => request.get("/api/experience/revisions").then(data),
  restore: (version, revision) =>
    request
      .post(`/api/experience/revisions/${version}/restore`, { revision })
      .then(data),
  sync: () => request.post("/api/experience/sync").then(data),
  document: (id) =>
    request
      .get(`/api/experience/documents/${encodeURIComponent(id)}`)
      .then(data),
  question: (key) =>
    request
      .get(`/api/experience/questions/${encodeURIComponent(key)}`)
      .then(data),
  progress: (key, body) =>
    request
      .put(
        `/api/experience/questions/${encodeURIComponent(key)}/progress`,
        body,
      )
      .then(data),
  review: (key, body) =>
    request
      .post(`/api/experience/questions/${encodeURIComponent(key)}/review`, body)
      .then(data),
  attempt: (key, body) =>
    request
      .post(
        `/api/experience/questions/${encodeURIComponent(key)}/attempts`,
        body,
        { timeout: 115000 },
      )
      .then(data),
};
