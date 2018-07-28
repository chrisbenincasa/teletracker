module.exports = {
  preset: "react-native",
  transform: {
    "^.+\\.ts$": "ts-jest",
    "^.+\\.tsx$": "ts-jest",
    "^.+\\.jsx$": "<rootDir>/node_modules/react-native/jest/preprocessor.js",
    "^.+\\.js$": "<rootDir>/node_modules/react-native/jest/preprocessor.js"
  },
  setupFiles: [
    "<rootDir>/Tests/Setup"
  ],
  testPathIgnorePatterns: [
    "/node_modules/",
    "<rootDir>/Tests/Setup.js"
  ],
  moduleNameMapper: {
    "^.+\\.(jpg|jpeg|png|gif|eot|otf|webp|svg|ttf|woff|woff2|mp4|webm|wav|mp3|m4a|aac|oga)$": "identity-obj-proxy"
  },
  testRegex: "(<rootDir>/Tests/.*|(\\.|/)(test|spec))\\.(jsx?|tsx?)$",
  moduleFileExtensions: ["ts", "tsx", "js", "jsx", "json", "node"],
};