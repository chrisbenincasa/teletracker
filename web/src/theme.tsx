import { responsiveFontSizes, createMuiTheme } from '@material-ui/core';
import { grey } from '@material-ui/core/colors';
import { hexToRGB } from './utils/style-utils';

// https://material-ui.com/customization/typography/#responsive-font-sizes
export default responsiveFontSizes(
  createMuiTheme({
    overrides: {
      MuiChip: {
        root: {
          borderRadius: 8,
        },
      },
    },
    palette: {
      primary: {
        main: '#00838f',
      },
      secondary: {
        main: grey[700],
      },
      type: 'dark',
    },
    custom: {
      borderRadius: {
        circle: '50%',
      },
      hover: {
        active: hexToRGB('#00838f', 0.6),
        opacity: 0.6,
      },
      backdrop: {
        backgroundColor: 'rgba(48, 48, 48, 0.5)',
        backgroundImage:
          'linear-gradient(to bottom, rgba(0, 0, 0, 0.5) 0%,rgba(48, 48, 48,1) 100%)',
      },
      palette: {
        cancel: '#e53935',
      },
    },
  }),
  { factor: 3 },
);
