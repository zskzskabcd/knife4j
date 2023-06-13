<template>
  <div>
    <Codemirror 
      :model-value="value" 
      @change="change" 
      :lang="lang" 
      :indent-with-tab="true"
      :tab-size="2"
      width="100%" 
      :extensions="extensions"
      style="max-height: 500px;min-height: 300px;"
    ></Codemirror>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue';
import { Codemirror } from 'vue-codemirror'
import { xml } from '@codemirror/lang-xml'
import { json } from '@codemirror/lang-json'
import { javascript } from '@codemirror/lang-javascript'
// import { oneDark } from '@codemirror/theme-one-dark'
import {ayuLight} from 'thememirror';

const props = defineProps({
  value: {
    type: [String, Object],
    required: true,
    default: ""
  },
  mode: {
    type: String,
    default: "json",
    required: true
  },
  debugResponse: {
    type: Boolean,
    default: false
  }
});
const emits = defineEmits(['update:value', 'debugEditorChange', 'showDescription']);


const valueText = ref(props.value)
watch(() => props.value, () => {
  valueText.value = props.value
})

const lang = ref("json");
const extensions = computed(() => {
  switch (props.mode) {
    case "json":
      return [json(), ayuLight];
    case "xml":
      return [xml(), ayuLight];
    case "javascript":
      return [javascript(), ayuLight];
  
    default:
      return [ayuLight]
  }
});
const change = (value) => {
  emits("update:value", value);
};
</script>
