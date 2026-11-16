export function timePart(value) {
  return value ? value.replace(' ', 'T').slice(11, 16) : ''
}

export function recordTimes(date, departure, arrival) {
  if (!date || !departure || !arrival || arrival <= departure) {
    throw new Error('到达时间须晚于出发时间')
  }
  return { departureTime: `${date}T${departure}`, arrivalTime: `${date}T${arrival}` }
}
