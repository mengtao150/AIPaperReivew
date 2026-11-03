<template><svg :width="size" :height="size" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path :d="paths[name] || paths.document" /></svg></template>
<script setup>
defineProps({ name: String, size: { type: [Number, String], default: 20 } })
const paths = {
  search: 'M21 21l-4.3-4.3M19 10.5a8.5 8.5 0 1 1-17 0 8.5 8.5 0 0 1 17 0',
  document: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z M14 2v6h6 M8 13h8 M8 17h5',
  folder: 'M3 7V5a2 2 0 0 1 2-2h5l2 3h7a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7z',
  settings: 'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8 M9 3l-1 3-3 1-2 4 2 3 1 3 4 2 3-1 3-1 2-4-1-3-1-3-4-2z',
  spark: 'M12 3l2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5z M20 2v4 M18 4h4',
  arrow: 'M5 12h14 M13 6l6 6-6 6', back: 'M19 12H5 M11 6l-6 6 6 6',
  external: 'M14 3h7v7 M10 14L21 3 M10 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-5',
  plus: 'M12 5v14 M5 12h14', check: 'M5 12l4 4L19 6', close: 'M6 6l12 12 M18 6L6 18',
  copy: 'M9 9h12v12H9z M15 9V3H3v12h6', translate: 'M3 5h12 M9 3v2 M6 5c0 6 4 10 8 11 M13 5c-1 5-4 9-10 12 M13 21l4-10 4 10 M15 17h4',
  connection: 'M8 12a4 4 0 0 1 0-6l3-3a4 4 0 0 1 6 6l-1 1 M16 12a4 4 0 0 1 0 6l-3 3a4 4 0 0 1-6-6l1-1 M8 16l8-8',
  edit: 'M16 3l5 5-13 13H3v-5z M13 6l5 5', trash: 'M3 6h18 M9 6V3h6v3 M5 6l1 15h12l1-15 M10 10v7 M14 10v7',
  info: 'M12 11v6 M12 7h.01 M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0',
  book: 'M12 5c-3-2-6-2-10-1v15c4-1 7-1 10 1 3-2 6-2 10-1V4c-4-1-7-1-10 1z M12 5v15',
  chart: 'M4 3v18h17 M8 17v-5 M13 17V7 M18 17v-8'
}
</script>
