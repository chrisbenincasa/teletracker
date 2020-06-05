import React, { useCallback, useEffect, useState } from 'react';
import {
  Button,
  createStyles,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  makeStyles,
  Theme,
} from '@material-ui/core';
import { OfflineBolt } from '@material-ui/icons';
import { logModalView } from '../../utils/analytics';

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    button: {
      margin: theme.spacing(1),
      whiteSpace: 'nowrap',
    },
    icon: {
      paddingRight: theme.spacing(0.5),
    },
    title: {
      backgroundColor: theme.palette.primary.main,
      padding: theme.spacing(1, 2),
    },
  }),
);

interface Props {
  open: boolean;
  onClose: () => void;
}

export default function SmartListDialog(props: Props) {
  const classes = useStyles();

  useEffect(() => {
    if (props.open) {
      logModalView('SmartListDialog');
    }
  }, [props.open]);

  const handleModalClose = () => {
    props.onClose();
  };

  return (
    <Dialog fullWidth maxWidth="xs" open={props.open}>
      <DialogTitle className={classes.title}>
        <OfflineBolt className={classes.icon} />
        What's a Smart List?
      </DialogTitle>
      <DialogContent>
        Smarts lists are dynamically generated lists. You specify the rules for
        the list and we'll do the work of always keeping your list up-to-date.
        So whether you want all of The Rock's movies from 2018 onward that have
        an IMDB rating of 7+ and are available on Netflix or you just want any
        movie that Keanu Reaves is in - we'll keep you updated. Smart list are
        entirely automated so once they are setup you are done. No need to
        manually add items - we take care of that for you!
      </DialogContent>
      <DialogActions>
        <Button
          onClick={handleModalClose}
          className={classes.button}
          color="primary"
          variant="contained"
        >
          Got It!
        </Button>
      </DialogActions>
    </Dialog>
  );
}
