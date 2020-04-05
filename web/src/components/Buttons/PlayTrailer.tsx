import React from 'react';
import { Button, makeStyles } from '@material-ui/core';
import { PlayArrow } from '@material-ui/icons';
import Thing from '../../types/Thing';

const useStyles = makeStyles(theme => ({
  buttonIcon: {
    [theme.breakpoints.down('sm')]: {
      fontSize: '1rem',
    },
    fontSIze: '2rem',
  },
  close: {
    padding: theme.spacing(0.5),
  },
  itemCTA: {
    whiteSpace: 'nowrap',
  },
}));

interface Props {
  style?: object;
  cta?: string;
  itemDetail: Thing;
}

export interface State {
  open: boolean;
}

export default function ShareButton(props: Props) {
  const classes = useStyles();

  return (
    <div className={classes.itemCTA} style={{ ...props.style }}>
      <Button
        size="small"
        variant="contained"
        fullWidth
        aria-label={'Play Trailer'}
        onClick={}
        startIcon={<PlayArrow className={classes.buttonIcon} />}
      >
        {props.cta || 'Play Trailer'}
      </Button>
    </div>
  );
}
