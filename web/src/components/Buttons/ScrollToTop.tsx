import React from 'react';
import { IconButton, Typography } from '@material-ui/core';
import { KeyboardArrowUp } from '@material-ui/icons';

interface Props {
  onClick: () => void;
  style?: object;
  className?: string;
}

export default function ScrollToTopButton(props: Props) {
  return (
    <IconButton
      onClick={props.onClick}
      style={props.style}
      className={props.className}
      size="medium"
    >
      <KeyboardArrowUp />
      <Typography variant="srOnly">Scroll to Top</Typography>
    </IconButton>
  );
}
