<template>
  <div>
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
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import { Codemirror } from 'vue-codemirror'
import { xml } from '@codemirror/lang-xml'
import { json } from '@codemirror/lang-json'
// import { oneDark } from '@codemirror/theme-one-dark'
import {ayuLight} from 'thememirror';

const props = defineProps({
  value: {
    type: [String, Object],
    required: true,
    default: ""
  },
  xmlMode: {
    type: Boolean,
    default: false,
    required: false
  }
});
const emits = defineEmits(['showDescription', 'change']);


const lang = ref("json");
const editorHeight = ref(200);
const extensions = computed(() => {
  if (lang.value == "xml") {
    return [xml(), ayuLight];
  } else {
    return [json(), ayuLight];
  }
});
const change = (value) => {
  emits("change", value);
};
const editorInit = () => {
  if (props.xmlMode) {
    lang.value = "xml";
  }
};
</script>
