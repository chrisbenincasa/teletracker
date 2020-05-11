import React from 'react';
import {
  createStyles,
  IconButton,
  makeStyles,
  Theme,
  Typography,
} from '@material-ui/core';
import { KeyboardArrowUp } from '@material-ui/icons';
import classNames from 'classnames';

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    scrollToTop: {
      backgroundColor: theme.palette.primary.main,
      '&:hover': {
        backgroundColor: theme.palette.primary.main,
      },
    },
  }),
);
interface Props {
  onClick: () => void;
  style?: object;
  className?: string;
  show: boolean;
}

export default function ScrollToTopButton(props: Props) {
  const classes = useStyles();

  return props.show ? (
    <IconButton
      onClick={props.onClick}
      style={props.style}
      className={classNames(props.className, classes.scrollToTop)}
      size="medium"
    >
      <KeyboardArrowUp />
      <Typography variant="srOnly">Scroll to Top</Typography>
    </IconButton>
  ) : null;
}
