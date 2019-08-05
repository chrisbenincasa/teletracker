import { Theme } from '@material-ui/core';
import { CSSProperties } from '@material-ui/core/styles/withStyles';

export const layoutStyles: (theme: Theme) => CSSProperties = (
  theme: Theme,
) => ({
  width: 'auto',
  marginTop: theme.spacing(1),
  marginLeft: theme.spacing(1),
  marginRight: theme.spacing(1),
  [theme.breakpoints.up(1100 + theme.spacing(4))]: {
    width: 1100,
    marginLeft: 'auto',
    marginRight: 'auto',
  },
});
