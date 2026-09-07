// Bot de protocolo real (mineflayer): se conecta a un servidor Paper/Folia
// de verdad como jugador real, ejecuta un comando, y reporta el chat recibido
// en JSON por stdout. Existe porque /voto40 exige un CSCommandSender que NO
// sea consola -- ni RCON ni la consola sirven para probarlo -- así que hasta
// 2026-09-06 el único camino documentado era "conecta con un cliente gráfico
// y mira a ver" (scripts/test-vote-scenarios.sh, escenario manual_vote40),
// que ningún test automático ejecutaba nunca.
//
// Ese hueco es justo el que dejó pasar el bug real de dispatchCommand()
// devolviendo siempre false en Bukkit/Paper/Folia (ver docs/BUGFIX-2026-09-06.md
// si existe, o el changelog del commit): 140 tests unitarios mockeaban
// dispatchCommand() a mano y el suite de Docker solo golpeaba el mock HTTP
// directamente con curl, nunca ejecutaba /voto40 de verdad. Este bot cierra
// ese hueco corriendo el comando real contra un servidor real.
//
// Uso: node vote-bot.js <host> <port> <username> <comando> [msTotal]
// Salida: una línea JSON por stdin -> stdout con {username, messages}.
const mineflayer = require('mineflayer')

const host = process.argv[2]
const port = parseInt(process.argv[3], 10)
const username = process.argv[4]
const command = process.argv[5] || 'voto40'
const totalMs = parseInt(process.argv[6] || '8000', 10)

if (!host || !port || !username) {
  console.error('Uso: node vote-bot.js <host> <port> <username> <comando> [msTotal]')
  process.exit(2)
}

const bot = mineflayer.createBot({ host, port, username, auth: 'offline', version: '1.20.4' })
const messages = []
let errored = null

bot.on('message', (jsonMsg) => messages.push(jsonMsg.toString()))
bot.on('error', (err) => { errored = err.message })
bot.on('kicked', (reason) => { errored = `kicked: ${reason}` })

function finish(code) {
  console.log(JSON.stringify({ username, messages, error: errored }))
  try { bot.quit() } catch (e) { /* ya desconectado */ }
  process.exit(code)
}

bot.once('spawn', () => {
  setTimeout(() => bot.chat(`/${command}`), 1500)
  setTimeout(() => finish(errored ? 1 : 0), totalMs)
})

bot.once('error', () => setTimeout(() => finish(1), 500))
