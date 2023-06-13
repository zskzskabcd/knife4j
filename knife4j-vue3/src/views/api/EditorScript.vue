<template>
  <Codemirror 
      :model-value="value" 
      @change="change" 
      @ready="editorInit" 
      :lang="lang" 
      :indent-with-tab="true"
      :tab-size="2"
      width="100%" 
      :extensions="extensions"
      :style="{'min-height': editorHeight + 'px'}"
    ></Codemirror>
</template>

<script setup>
import { ref, computed } from 'vue';
import { Codemirror } from 'vue-codemirror'
import { javascript } from '@codemirror/lang-javascript'
// import { oneDark } from '@codemirror/theme-one-dark'
import {ayuLight} from 'thememirror';

const props = defineProps({
  value: {
    type: [String, Object],
    required: true,
    default: ""
  },
  tsMode: {
    type: Boolean,
    required: false,
    default: false,
  }
})

const emits = defineEmits(['change', 'showDescription'])


const lang = ref("javascript");
const editorHeight = ref(200);
const extensions = computed(() => {
  if (lang.value == "typescript") {
    return [javascript(), ayuLight];
  } else {
    return [javascript(), ayuLight];
  }
});

const change = (value) => {
  emits('change', value)
}

const editorInit = () => {
  if (props.tsMode) {
    lang.value = "typescript"
  }
}
</script>
