import React, { useEffect, useRef, useState } from 'react';
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  FormHelperText,
  IconButton,
  makeStyles,
  Snackbar,
  TextField,
} from '@material-ui/core';
import { Close } from '@material-ui/icons';
import _ from 'lodash';
import {
  FacebookIcon,
  FacebookShareButton,
  RedditIcon,
  RedditShareButton,
  TumblrIcon,
  TumblrShareButton,
  TwitterIcon,
  TwitterShareButton,
} from 'react-share';

const useStyles = makeStyles(theme => ({
  button: {
    margin: theme.spacing(1),
    whiteSpace: 'nowrap',
  },
  close: {
    padding: theme.spacing(0.5),
  },
  filterContainer: {
    marginTop: theme.spacing(1),
  },
  shareButton: {
    margin: theme.spacing(1),
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
  },
  shareButtonContainer: {
    display: 'flex',
    flexDirection: 'row',
  },
  snackbarContainer: {
    position: 'fixed',
    display: 'flex',
    justifyContent: 'center',
    bottom: 0,
    zIndex: theme.zIndex.tooltip,
    margin: theme.spacing(1),
  },
  title: {
    backgroundColor: theme.palette.primary.main,
    padding: theme.spacing(1, 2),
  },
  urlField: {
    margin: theme.spacing(1),
  },
}));

interface Props {
  open: boolean;
  onClose: () => void;
  url: string;
  title: string;
}

export default function CreateDynamicListDialog(props: Props) {
  const classes = useStyles();
  let [snackbarOpen, setSnackbarOpen] = useState(false);
  const urlField = useRef<HTMLInputElement>(null);

  const handleModalClose = () => {
    props.onClose();
  };

  const handleCopy = () => {
    urlField?.current?.select();
    document.execCommand('copy');

    props.onClose();
    setSnackbarOpen(true);
  };

  return (
    <React.Fragment>
      <Dialog
        aria-labelledby="share-dialog"
        aria-describedby="share-dialog"
        open={props.open}
        onClose={handleModalClose}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle id="share-dialog" className={classes.title}>
          Share
        </DialogTitle>
        <DialogContent>
          <div className={classes.shareButtonContainer}>
            {/*
// @ts-ignore: https://github.com/nygardk/react-share/issues/277 */}
            <TwitterShareButton
              url={props.url}
              title={props.title}
              className={classes.shareButton}
            >
              {/*
// @ts-ignore: https://github.com/nygardk/react-share/issues/277 */}
              <TwitterIcon size={64} round={true} />
              Twitter
            </TwitterShareButton>

            {/*
// @ts-ignore: https://github.com/nygardk/react-share/issues/277 */}
            <FacebookShareButton
              url={props.url}
              quote={props.title}
              className={classes.shareButton}
            >
              {/*
// @ts-ignore: https://github.com/nygardk/react-share/issues/277 */}
              <FacebookIcon size={64} round={true} />
              Facebook
            </FacebookShareButton>
            {/*
        // @ts-ignore: https://github.com/nygardk/react-share/issues/277 */}
            <RedditShareButton
              url={props.url}
              title={props.title}
              windowWidth={660}
              windowHeight={460}
              className={classes.shareButton}
            >
              {/*
// @ts-ignore: https://github.com/nygardk/react-share/issues/277 */}
              <RedditIcon size={64} round={true} />
              Reddit
            </RedditShareButton>

            {/*       
        // @ts-ignore: https://github.com/nygardk/react-share/issues/277 */}
            <TumblrShareButton
              url={props.url}
              title={props.title}
              className={classes.shareButton}
            >
              {/*
// @ts-ignore: https://github.com/nygardk/react-share/issues/277 */}
              <TumblrIcon size={64} round={true} />
              Tumblr
            </TumblrShareButton>
          </div>
          <FormControl style={{ width: '100%' }}>
            <TextField
              autoFocus
              margin="dense"
              id="name"
              label="URL"
              type="text"
              fullWidth
              value={props.url}
              InputProps={{
                readOnly: true,
              }}
              inputRef={urlField}
              className={classes.urlField}
            />
          </FormControl>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleModalClose} className={classes.button}>
            Cancel
          </Button>
          <Button
            onClick={handleCopy}
            color="primary"
            variant="contained"
            className={classes.button}
          >
            Copy URL
          </Button>
        </DialogActions>
      </Dialog>
      <Snackbar
        anchorOrigin={{
          vertical: 'bottom',
          horizontal: 'center',
        }}
        open={snackbarOpen}
        autoHideDuration={5000}
        onClose={() => setSnackbarOpen(false)}
        onExited={() => setSnackbarOpen(false)}
        className={classes.snackbarContainer}
        message={'URL copied to clipboard'}
        action={
          <React.Fragment>
            <IconButton
              aria-label="close"
              color="inherit"
              className={classes.close}
              onClick={() => setSnackbarOpen(false)}
            >
              <Close />
            </IconButton>
          </React.Fragment>
        }
      />
    </React.Fragment>
  );
}
