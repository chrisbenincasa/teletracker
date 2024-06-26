import React, { useRef } from 'react';
import { Button, IconButton, Input, makeStyles } from '@material-ui/core';
import { Close, Share } from '@material-ui/icons';
import ShareDialog from '../Dialogs/ShareDialog';

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
  title: string;
  text: string;
  url: string;
  className?: string;
}

export interface SnackbarMessage {
  message: string;
  key: number;
}

export interface State {
  open: boolean;
}

export default function ShareButton(props: Props) {
  const classes = useStyles();
  const [open, setOpen] = React.useState(false);

  const share = event => {
    let newVariable: any;

    newVariable = window.navigator;

    if (newVariable?.share) {
      newVariable
        .share({
          title: props.title,
          text: props.text,
          url: props.url,
        })
        .then(() => {
          // GA track total shares?
        })
        .catch(error => {
          // GA track errors?
        });
    } else {
      setOpen(true);
      // GA track copies?
    }
  };

  return (
    <div className={props.className} style={{ ...props.style }}>
      <Button
        size="small"
        variant="contained"
        fullWidth
        aria-label={'Share'}
        onClick={event => share(event)}
        startIcon={<Share className={classes.buttonIcon} />}
        className={classes.itemCTA}
      >
        {props.cta || 'Share'}
      </Button>
      <ShareDialog
        onClose={() => setOpen(false)}
        title={props.title}
        open={open}
        url={props.url}
      />
    </div>
  );
}
