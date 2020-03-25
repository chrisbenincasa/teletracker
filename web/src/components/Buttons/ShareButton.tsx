import React, { useRef } from 'react';
import {
  Button,
  IconButton,
  Input,
  makeStyles,
  Snackbar,
} from '@material-ui/core';
import { Close, Share } from '@material-ui/icons';

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
  snackbarContainer: {
    position: 'fixed',
    display: 'flex',
    justifyContent: 'center',
    bottom: 0,
    zIndex: theme.zIndex.tooltip,
    margin: theme.spacing(1),
  },
}));

interface Props {
  style?: object;
  cta?: string;
  title: string;
  text: string;
  url: string;
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
  const hiddenURLField = useRef<HTMLInputElement>(null);

  // Borrowing some open source code for generating hidden input
  // https://github.com/CharlesStover/use-clippy/blob/e763fe557ce20faa5cf8d7e54d2262886dae98c2/src/use-clippy.ts#L65
  const zeroStyles = (i: HTMLInputElement, ...properties: string[]): void => {
    for (const property of properties) {
      i.style.setProperty(property, '0');
    }
  };

  const createInput = (): HTMLInputElement => {
    const i: HTMLInputElement = document.createElement('input');
    i.setAttribute('size', '0');
    zeroStyles(
      i,
      'border-width',
      'bottom',
      'margin-left',
      'margin-top',
      'outline-width',
      'padding-bottom',
      'padding-left',
      'padding-right',
      'padding-top',
      'right',
    );
    i.style.setProperty('box-sizing', 'border-box');
    i.style.setProperty('height', '1px');
    i.style.setProperty('margin-bottom', '-1px');
    i.style.setProperty('margin-right', '-1px');
    i.style.setProperty('max-height', '1px');
    i.style.setProperty('max-width', '1px');
    i.style.setProperty('min-height', '1px');
    i.style.setProperty('min-width', '1px');
    i.style.setProperty('outline-color', 'transparent');
    i.style.setProperty('position', 'absolute');
    i.style.setProperty('width', '1px');
    document.body.appendChild(i);
    return i;
  };

  const removeInput = (i: HTMLInputElement): void => {
    document.body.removeChild(i);
  };

  const copyToClipboard = () => {
    const i = createInput();
    i.setAttribute('value', props.url);
    i.select();
    const success = document.execCommand('copy');
    removeInput(i);
    if (!success) {
      console.log('error copying');
    }
  };

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
      handleShare();
      // GA track copies?
    }
  };

  const handleShare = () => {
    copyToClipboard();
    setOpen(true);
  };

  const handleClose = () => {
    setOpen(false);
  };

  return (
    <div className={classes.itemCTA} style={{ ...props.style }}>
      <Button
        size="small"
        variant="contained"
        fullWidth
        aria-label={'Share'}
        onClick={() => share(event)}
        startIcon={<Share className={classes.buttonIcon} />}
      >
        {props.cta || 'Share'}
      </Button>
      <Input
        id="url"
        name="url"
        type="hidden"
        value={props.url}
        ref={hiddenURLField}
      />
      <Snackbar
        anchorOrigin={{
          vertical: 'bottom',
          horizontal: 'center',
        }}
        open={open}
        autoHideDuration={5000}
        onClose={handleClose}
        onExited={handleClose}
        className={classes.snackbarContainer}
        message={'URL copied to clipboard'}
        action={
          <React.Fragment>
            <IconButton
              aria-label="close"
              color="inherit"
              className={classes.close}
              onClick={handleClose}
            >
              <Close />
            </IconButton>
          </React.Fragment>
        }
      />
    </div>
  );
}
