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
import { Public } from '@material-ui/icons';

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

export default function PublicListDialog(props: Props) {
  const classes = useStyles();

  const handleModalClose = () => {
    props.onClose();
  };

  return (
    <Dialog fullWidth maxWidth="xs" open={props.open}>
      <DialogTitle className={classes.title}>
        <Public className={classes.icon} />
        This is a Public List
      </DialogTitle>
      <DialogContent>
        Public lists can be viewed by anyone who has the URL. Meanwhile, private
        lists are only viewable by their creator. This setting can be adjusted
        for any list you created.
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
