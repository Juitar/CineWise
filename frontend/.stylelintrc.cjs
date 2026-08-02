const umiStylelintConfig = require('@umijs/lint/dist/config/stylelint');

module.exports = {
  ...umiStylelintConfig,
  rules: {
    ...umiStylelintConfig.rules,
    'declaration-empty-line-before': null,
    'selector-class-pattern': [
      '^[a-z][a-z0-9]*(?:-[a-z0-9]+)*(?:--[a-z0-9]+(?:-[a-z0-9]+)*)?$',
      {
        message: 'Expected class selector to use kebab-case with an optional modifier',
      },
    ],
  },
};
