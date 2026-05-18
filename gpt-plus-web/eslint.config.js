import js from '@eslint/js'
import eslintConfigPrettier from 'eslint-config-prettier/flat'
import tseslint from 'typescript-eslint'
import vue from 'eslint-plugin-vue'

export default [
  {
    ignores: ['dist', 'node_modules', 'coverage', '*.tsbuildinfo'],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  ...vue.configs['flat/recommended'],
  {
    files: ['**/*.{ts,vue}'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: {
        cancelAnimationFrame: 'readonly',
        console: 'readonly',
        document: 'readonly',
        Event: 'readonly',
        fetch: 'readonly',
        HTMLElement: 'readonly',
        HTMLButtonElement: 'readonly',
        navigator: 'readonly',
        requestAnimationFrame: 'readonly',
        ScrollBehavior: 'readonly',
        TextDecoder: 'readonly',
        URL: 'readonly',
        window: 'readonly',
      },
    },
  },
  {
    files: ['**/*.vue'],
    languageOptions: {
      parserOptions: {
        parser: tseslint.parser,
        extraFileExtensions: ['.vue'],
      },
    },
    rules: {
      'vue/attribute-hyphenation': [
        'error',
        'always',
        {
          ignore: ['row-col'],
        },
      ],
      'vue/multi-word-component-names': 'off',
      'vue/no-v-html': 'off',
    },
  },
  eslintConfigPrettier,
]
