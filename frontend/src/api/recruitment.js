import request from '@/utils/request'

const CACHE_TTL = 60_000
const FACET_CACHE_TTL = 300_000
const responseCache = new Map()
let facetCache = null

function normalizedParams(params) {
  return {
    page: Number(params.page) || 1,
    size: Number(params.size) || 30,
    keyword: params.keyword || '',
    recruitmentType: params.recruitmentType || '',
    companyType: params.companyType || '',
    city: params.city || '',
    freshOnly: Boolean(params.freshOnly),
    industry: params.industry || '',
    sourceKind: params.sourceKind || '',
    targetGraduates: params.targetGraduates || '',
    publishedWithinDays: Number(params.publishedWithinDays) || 0,
    officialOnly: Boolean(params.officialOnly),
    sort: params.sort || 'latest',
  }
}

function cacheKey(params) {
  return JSON.stringify(params)
}

export const recruitmentApi = {
  async list(params = {}, options = {}) {
    const normalized = normalizedParams(params)
    const key = cacheKey(normalized)
    const cached = responseCache.get(key)
    if (!options.force && cached && Date.now() - cached.createdAt < CACHE_TTL) {
      return cached.data
    }

    const response = await request.get('/api/recruitments', {
      params: normalized,
      signal: options.signal,
      timeout: 15_000,
    })
    const data = response.data?.data || { items: [], total: 0, page: 1, size: 30, hasMore: false }
    responseCache.set(key, { createdAt: Date.now(), data })
    return data
  },

  async facets(options = {}) {
    if (!options.force && facetCache && Date.now() - facetCache.createdAt < FACET_CACHE_TTL) {
      return facetCache.data
    }
    const response = await request.get('/api/recruitments/facets', {
      signal: options.signal,
      timeout: 15_000,
    })
    const data = response.data?.data || {}
    facetCache = { createdAt: Date.now(), data }
    return data
  },

  clearCache() {
    responseCache.clear()
    facetCache = null
  },
}
