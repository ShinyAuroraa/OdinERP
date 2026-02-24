export async function GET() {
  const mem = process.memoryUsage()
  const uptime = process.uptime()

  const lines = [
    '# HELP process_uptime_seconds Process uptime in seconds',
    '# TYPE process_uptime_seconds gauge',
    `process_uptime_seconds ${uptime.toFixed(2)}`,
    '',
    '# HELP process_heap_bytes Node.js heap usage in bytes',
    '# TYPE process_heap_bytes gauge',
    `process_heap_bytes{type="used"} ${mem.heapUsed}`,
    `process_heap_bytes{type="total"} ${mem.heapTotal}`,
    '',
    '# HELP process_rss_bytes Resident Set Size in bytes',
    '# TYPE process_rss_bytes gauge',
    `process_rss_bytes ${mem.rss}`,
    '',
  ]

  return new Response(lines.join('\n'), {
    status: 200,
    headers: {
      'Content-Type': 'text/plain; version=0.0.4; charset=utf-8',
    },
  })
}
