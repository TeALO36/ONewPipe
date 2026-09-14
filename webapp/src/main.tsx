import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './theme.css'
import App from './App.tsx'
import { startAccountSync } from './account'
import { resumeDownloads } from './downloads'

startAccountSync()
resumeDownloads()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
