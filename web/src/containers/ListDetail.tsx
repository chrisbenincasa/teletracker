import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  FormControl,
  FormControlLabel,
  IconButton,
  LinearProgress,
  ListItemIcon,
  Menu,
  MenuItem,
  Switch,
  TextField,
  Tooltip,
  Typography,
} from '@material-ui/core';
<<<<<<< HEAD
=======
import {
  Delete,
  Edit,
  OfflineBolt,
  Public,
  Settings,
} from '@material-ui/icons';
import _ from 'lodash';
import React, { useCallback, useContext, useEffect, useState } from 'react';
import InfiniteScroll from 'react-infinite-scroller';
>>>>>>> 92296f59... more cleaning
import {
  Delete,
  Edit,
  OfflineBolt,
  Public,
  Settings,
} from '@material-ui/icons';
import _ from 'lodash';
import React, { useCallback, useEffect, useState } from 'react';
import { LIST_RETRIEVE_INITIATED, getList, updateList } from '../actions/lists';
import ShowFiltersButton from '../components/Buttons/ShowFiltersButton';
import ScrollToTopContainer from '../components/ScrollToTopContainer';
import { List } from '../types';
import { smartListRulesToFilters } from '../utils/list-utils';
import { useRouter } from 'next/router';
import useStateSelector, {
  useStateSelectorWithPrevious,
} from '../hooks/useStateSelector';
import { useWithUserContext } from '../hooks/useWithUser';
import { useDispatchAction } from '../hooks/useDispatchAction';
import { hookDeepEqual } from '../hooks/util';
<<<<<<< HEAD
import WithItemFilters from '../components/Filters/FilterContext';
import selectList from '../selectors/selectList';
import useStyles from '../components/ListDetail/ListDetail.styles';
import ListDetailDialog from '../components/ListDetail/ListDetailDialog';
import ListItems from '../components/ListDetail/ListItems';
import { useNetworks } from '../hooks/useStateMetadata';
import { FilterParams } from '../utils/searchFilters';
import useMemoCompare from '../hooks/useMemoCompare';
import { usePrevious } from '../hooks/usePrevious';
import SmartListDialog from '../components/Dialogs/SmartListDialog';
import PublicListDialog from '../components/Dialogs/PublicListDialog';
=======
import { FilterContext } from '../components/Filters/FilterContext';
import useFilterLoadEffect from '../hooks/useFilterLoadEffect';
import { filterParamsEqual } from '../utils/changeDetection';
import SmartListDialog from '../components/Dialogs/SmartListDialog';
import PublicListDialog from '../components/Dialogs/PublicListDialog';

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    listHeader: {
      margin: theme.spacing(2, 0),
      display: 'flex',
      flex: '1 0 auto',
      alignItems: 'center',
    },
    listName: {
      display: 'flex',
      textDecoration: 'none',
      '&:focus, &:hover, &:visited, &:link &:active': {
        color: theme.palette.text.primary,
      },
    },
    listNameContainer: {
      display: 'flex',
      flex: '1 0 auto',
    },
    loadingCircle: {
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      minHeight: 200,
      height: '100%',
    },
    filters: {
      display: 'flex',
      flexDirection: 'row',
      marginBottom: theme.spacing(1),
      justifyContent: 'flex-end',
      alignItems: 'center',
    },
    formControl: {
      margin: theme.spacing(1),
      minWidth: 120,
      display: 'flex',
    },
    icon: {
      margin: theme.spacing(0, 1),
    },
    listContainer: {
      display: 'flex',
      flexDirection: 'column',
      padding: theme.spacing(0, 2),
      width: '100%',
    },
    noContent: {
      margin: theme.spacing(5),
      width: '100%',
    },
    root: {
      display: 'flex',
      flexGrow: 1,
    },
    textField: {
      margin: theme.spacing(0, 1),
      width: 200,
    },
    title: {
      backgroundColor: theme.palette.primary.main,
      padding: theme.spacing(1, 2),
    },
    urlField: {
      margin: theme.spacing(1),
    },
  }),
);
>>>>>>> 92296f59... more cleaning

interface ListDetailDialogProps {
  list?: List;
  openDeleteConfirmation: boolean;
}

interface ListDetailProps {
  readonly preloaded?: boolean;
}

function ListDetail(props: ListDetailProps) {
  const classes = useStyles();

  const { isLoggedIn } = useWithUserContext();
  const [anchorEl, setAnchorEl] = useState<HTMLElement | undefined>();
  const [deleted, setDeleted] = useState(false);
  const [deleteConfirmationOpen, setDeleteConfirmationOpen] = useState(false);
  const [renameDialogOpen, setRenameDialogOpen] = useState(false);
  const [publicListDialogOpen, setPublicListDialogOpen] = useState(false);
<<<<<<< HEAD
  const [publicListOptionsOpen, setPublicListOptionsOpen] = useState(false);
=======
>>>>>>> 92296f59... more cleaning
  const [smartListDialogOpen, setSmartListDialogOpen] = useState(false);
  const [publicList, setPublicList] = useState(false);
  const [deleteOnWatch, setDeleteOnWatch] = useState(false);
  const [newListName, setNewListName] = useState('');
  const router = useRouter();
  const networks = useNetworks();

  const listId = router.query.id as string;
  const previousListId = usePrevious(listId);

  const listLoading = useStateSelector(
    state => state.lists.loading[LIST_RETRIEVE_INITIATED],
  );

  const [list, previousList] = useStateSelectorWithPrevious(
    state => selectList(state, listId),
    hookDeepEqual,
  );

  let defaultFilters: FilterParams = useMemoCompare(
    () => {
      if (list && networks) {
        return smartListRulesToFilters(list, networks || []);
      } else {
        return {};
      }
    },
    [list, networks],
    hookDeepEqual,
  );

  const [showFilter, setShowFilter] = useState(false);

  const dispatchUpdateList = useDispatchAction(updateList);
<<<<<<< HEAD
  const dispatchGetList = useDispatchAction(getList);
=======
  const dispatchRetrieveList = useDispatchAction(ListRetrieveInitiated);

  const handleModalClose = useCallback(() => {
    setSmartListDialogOpen(false);
    setPublicListDialogOpen(false);
  }, []);

  const openSmartListDialog = useCallback(() => {
    setSmartListDialogOpen(true);
  }, []);

  const openPublicListDialog = useCallback(() => {
    setPublicListDialogOpen(true);
  }, []);

  const retrieveList = (initialLoad: boolean, force: boolean = true) => {
    dispatchRetrieveList({
      listId: listId,
      force,
      limit: calculateLimit(width, 3),
      bookmark: listBookmark,
      ...makeListFilters(initialLoad, filters, listFilters),
      sort: filters.sortOrder,
      itemTypes: filters.itemTypes,
      genres: filters.genresFilter,
      networks: filters.networks,
    });
  };
>>>>>>> 92296f59... more cleaning

  const handleModalClose = useCallback(() => {
    setSmartListDialogOpen(false);
    setPublicListDialogOpen(false);
    setPublicListOptionsOpen(false);
  }, []);

  const openSmartListDialog = useCallback(() => {
    setSmartListDialogOpen(true);
  }, []);

  const openPublicListDialog = useCallback(() => {
    setPublicListDialogOpen(true);
  }, []);

  const openPublicListOptions = useCallback(() => {
    setPublicListOptionsOpen(true);
  }, []);

  const handleRenameList = () => {
    if (isLoggedIn) {
      dispatchUpdateList({
        listId: listId,
        name: newListName,
      });
    }

    setRenameDialogOpen(false);
  };

  const handleMakeListPublic = () => {
    if (isLoggedIn) {
      // dispatchUpdateList({
      //   listId: listId,
      //   name: newListName,
      // });
    }

    setPublicListDialogOpen(false);
  };

  const handleRenameChange = event => {
    setNewListName(event.target.value);
  };

  //
  // Effects
  //

  useEffect(() => {
    if ((previousListId || !props.preloaded) && listId !== previousListId) {
      dispatchGetList({ listId });
    }
  }, [listId, previousListId, props.preloaded]);

  useEffect(() => {
    if (!previousList && list) {
      setDeleteOnWatch(prev => {
        let removeWatchedItemsOption =
          list?.configuration?.options?.removeWatchedItems;
        if (_.isUndefined(removeWatchedItemsOption)) {
          return prev;
        } else {
          return removeWatchedItemsOption;
        }
      });
    }
  }, [list]);

  //
  // State updaters
  //

  const handleMenu = event => {
    setAnchorEl(event.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(undefined);
  };

  const setWatchedSetting = () => {
    if (list) {
      const listOptions = list.configuration?.options || {};
      let newListOptions = {
        ...listOptions,
        removeWatchedItems: !deleteOnWatch,
      };

      dispatchUpdateList({
        listId,
        options: newListOptions,
      });

      setDeleteOnWatch(prev => !prev);
    }
  };

  const handleRenameModalOpen = () => {
    handleClose();
    setRenameDialogOpen(true);
  };

  const toggleFilters = () => {
    setShowFilter(prev => !prev);
  };

  //
  // Render
  //
  const renderLoading = () => {
    return (
      <div style={{ display: 'flex' }}>
        <div style={{ flexGrow: 1 }}>
          <LinearProgress />
        </div>
      </div>
    );
  };

  const renderRenameDialog = (list: List) => {
    if (!list) {
      return;
    }

    return (
      <div>
        <Dialog
          open={renameDialogOpen}
          onClose={() => setRenameDialogOpen(false)}
          aria-labelledby="alert-dialog-title"
          aria-describedby="alert-dialog-description"
        >
          <DialogTitle id="alert-dialog-title" className={classes.title}>
            {`Update "${list?.name}" List Name?`}
          </DialogTitle>
          <DialogContent>
            <DialogContentText id="alert-dialog-description">
              Naming is hard, update your list name:
            </DialogContentText>
            <FormControl className={classes.formControl}>
              <TextField
                label="List Name"
                defaultValue={list.name}
                className={classes.textField}
                margin="normal"
                onChange={handleRenameChange}
              />
            </FormControl>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setRenameDialogOpen(false)}>Cancel</Button>
            <Button
              onClick={handleRenameList}
              color="primary"
              variant="contained"
              autoFocus
            >
              Update
            </Button>
          </DialogActions>
        </Dialog>
      </div>
    );
  };

  const renderMakeListPublic = (list: List) => {
    if (!list) {
      return;
    }

    return (
      <div>
        <Dialog
          open={publicListOptionsOpen}
          onClose={() => setPublicListDialogOpen(false)}
          aria-labelledby="alert-dialog-public-list"
          aria-describedby="alert-dialog-description"
        >
          <DialogTitle id="alert-dialog-public-list" className={classes.title}>
            {`Make "${list?.name}" List Public?`}
          </DialogTitle>
          <DialogContent>
            <DialogContentText id="alert-dialog-description">
              Making this list public will allow anyone with the URL to view
              your list.
            </DialogContentText>
            <FormControlLabel
              control={
                <Switch
                  checked={list.isPublic}
                  onChange={() => handleMakeListPublic()}
                  value="checked"
                  color="primary"
                />
              }
              label="Public List"
            />
            <FormControl style={{ width: '100%' }}>
              <TextField
                autoFocus
                margin="dense"
                id="name"
                label="URL"
                type="text"
                fullWidth
                value={window.location.href}
                InputProps={{
                  readOnly: true,
                }}
                className={classes.urlField}
              />
            </FormControl>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setPublicListOptionsOpen(false)}>
              Cancel
            </Button>
            <Button
              onClick={handleMakeListPublic}
              color="primary"
              variant="contained"
              autoFocus
            >
              Update
            </Button>
          </DialogActions>
        </Dialog>
      </div>
    );
  };

  const renderProfileMenu = () => {
    if (!isLoggedIn) {
      return null;
    }

    if (!list?.ownedByRequester) {
      return null;
    }

    let isMenuOpen = !_.isUndefined(anchorEl);

    return (
      <div>
        <IconButton
          aria-owns={isMenuOpen ? 'material-appbar' : undefined}
          aria-haspopup="true"
          color="inherit"
          onClick={handleMenu}
        >
          <Settings />
          <Typography variant="srOnly">Settings</Typography>
        </IconButton>
        <Menu
          anchorEl={anchorEl}
          anchorOrigin={{ vertical: 'top', horizontal: 'right' }}
          transformOrigin={{ vertical: 'top', horizontal: 'right' }}
          open={isMenuOpen}
          onClose={handleClose}
          disableAutoFocusItem
        >
          <MenuItem onClick={handleRenameModalOpen}>
            <ListItemIcon>
              <Edit />
            </ListItemIcon>
            Rename List
          </MenuItem>
          <MenuItem onClick={() => setDeleteConfirmationOpen(true)}>
            <ListItemIcon>
              <Delete />
            </ListItemIcon>
            Delete List
          </MenuItem>
          <MenuItem>
            <FormControlLabel
              control={
                <Switch
                  checked={deleteOnWatch}
                  onChange={setWatchedSetting}
                  value="checked"
                  color="primary"
                />
              }
              label="Automatically remove items after watching"
            />
          </MenuItem>
<<<<<<< HEAD
          <MenuItem onClick={() => setPublicListOptionsOpen(true)}>
=======
          {/* <MenuItem onClick={() => setPublicListDialogOpen(true)}>
>>>>>>> b61fd0d8... commented out make list public
            <ListItemIcon>
              <Public />
            </ListItemIcon>
            {`Make list ${list.isPublic ? 'Private' : 'Public'}`}
          </MenuItem> */}
        </Menu>
      </div>
    );
  };

  const renderListDetail = (list: List) => {
    if ((!listLoading && !list) || deleted) {
      router.replace('/');
    }

    return (
      <div className={classes.root}>
        <div className={classes.listContainer}>
          <div className={classes.listHeader}>
            <div className={classes.listNameContainer}>
              <Typography
                variant="h4"
                align="left"
                className={classes.listName}
              >
                {list.name}
                {list.isPublic ? (
                  <Tooltip title="This list is public" placement="top">
                    <IconButton onClick={openPublicListDialog} size="small" gst>
                      <Public className={classes.icon} />
                    </IconButton>
                  </Tooltip>
                ) : null}
                {list.isDynamic ? (
                  <Tooltip title="This is a Smart List" placement="top">
                    <IconButton onClick={openSmartListDialog} size="small">
                      <OfflineBolt className={classes.icon} />
                    </IconButton>
                  </Tooltip>
                ) : null}
              </Typography>
            </div>
            <ShowFiltersButton onClick={toggleFilters} />
            {renderProfileMenu()}
          </div>
          <WithItemFilters initialFilters={{ ...defaultFilters }}>
            <ListItems showFilter={showFilter} listId={listId} />
          </WithItemFilters>
        </div>
        <ListDetailDialog
          list={list}
          openDeleteConfirmation={deleteConfirmationOpen}
        />
        <SmartListDialog
          open={smartListDialogOpen}
          onClose={handleModalClose}
        />
        <PublicListDialog
          open={publicListDialogOpen}
          onClose={handleModalClose}
        />
        {renderRenameDialog(list)}
        {renderMakeListPublic(list)}
      </div>
    );
  };

  return list && networks ? (
    <ScrollToTopContainer>{renderListDetail(list!)}</ScrollToTopContainer>
  ) : (
    renderLoading()
  );
}

// DEBUG only.
// ListDetail.whyDidYouRender = true;

export default React.memo(ListDetail, _.isEqual);
