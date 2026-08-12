import request from '@/utils/request'

function data(response, fallback) {
  return response?.data?.data ?? fallback
}

export const applicationApi = {
  async list(params = {}) {
    return data(await request.get('/api/applications', { params }), { items: [], summary: {} })
  },
  async create(payload) {
    return data(await request.post('/api/applications', payload), null)
  },
  async addFromRecruitment(recruitmentId) {
    return data(await request.post('/api/applications/from-recruitment', { recruitmentId }), null)
  },
  async update(id, payload) {
    return data(await request.put(`/api/applications/${id}`, payload), null)
  },
  async remove(id) {
    return data(await request.delete(`/api/applications/${id}`), null)
  },
}

export const knowledgeApi = {
  async list() {
    return data(await request.get('/api/knowledge'), [])
  },
  async createText(payload) {
    return data(await request.post('/api/knowledge/text', payload), null)
  },
  async upload(file, title = '') {
    const form = new FormData()
    form.append('file', file)
    if (title) form.append('title', title)
    return data(await request.post('/api/knowledge/upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 90_000,
    }), null)
  },
  async remove(id) {
    return data(await request.delete(`/api/knowledge/${id}`), null)
  },
}

export const mediaApi = {
  async ocr(file) {
    const form = new FormData()
    form.append('file', file)
    return data(await request.post('/api/media/ocr', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 45_000,
    }), { text: '' })
  },
}
