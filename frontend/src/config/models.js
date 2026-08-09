export const CHAT_MODEL_STORAGE_KEY = 'chatModelId'

export const CHAT_MODELS = Object.freeze([
  {
    id: 'zhipu-glm-4.7-flash',
    label: 'GLM-4.7 Flash',
    provider: 'zhipu',
    modelName: 'GLM-4.7-Flash',
    description: '快速回答'
  },
  {
    id: 'deepseek-v4-flash',
    label: 'DeepSeek V4 Flash',
    provider: 'deepseek',
    modelName: 'deepseek-v4-flash',
    description: '快速对话'
  },
  {
    id: 'deepseek-v4-pro',
    label: 'DeepSeek V4 Pro',
    provider: 'deepseek',
    modelName: 'deepseek-v4-pro',
    description: '复杂任务'
  }
])

export const DEFAULT_CHAT_MODEL_ID = 'deepseek-v4-flash'

export function getChatModel(modelId) {
  return CHAT_MODELS.find(model => model.id === modelId)
    || CHAT_MODELS.find(model => model.id === DEFAULT_CHAT_MODEL_ID)
}
