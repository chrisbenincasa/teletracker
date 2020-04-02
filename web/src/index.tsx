import React from 'react';
import { Theme } from '@material-ui/core';
import 'sanitize.css/sanitize.css';
import './index.css';

declare module '@material-ui/core/styles/createMuiTheme' {
  interface Theme {
    custom: {
      borderRadius: {
        circle: string;
      };
      hover: {
        active: string;
        opacity: number;
      };
      backdrop: {
        backgroundColor: string;
        backgroundImage: string;
      };
      palette: {
        cancel: string;
      };
    };
  }
  // allow configuration using `createMuiTheme`
  interface ThemeOptions {
    custom?: {
      borderRadius?: {
        circle?: string;
      };
      hover?: {
        active?: string;
        opacity?: number;
      };
      backdrop?: {
        backgroundColor?: string;
        backgroundImage?: string;
      };
      palette?: {
        cancel?: string;
      };
    };
  }
}
